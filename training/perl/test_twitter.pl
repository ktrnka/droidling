use warnings;
use LangIDTable;
use HCParser;
use IO::Handle;
use Getopt::Long;

STDOUT->autoflush(1);

my $batch_size = 2;
my $showHelp = 0;

GetOptions('batch=i' => \$batch_size,
	'predictor=s' => \$LangIDTable::predictor,
	'help' => \$showHelp);

if ($showHelp)
	{
	print <<HERE;
perl $0 [options] LangID_model_dir Twitter_Manifest

Test the language ID models in specified dir against
the Twitter samples specified in the Twitter Manifest.
HERE
	exit;
	}

my ($model_dir, @hc_twitter_files) = @ARGV;

opendir $model_dh, $model_dir;
my @model_files = readdir $model_dh;
closedir $model_dh;

@model_files = grep /\.lid$/, @model_files;
my $lang_codes = {};

print "Loading models...\n";
foreach my $file (@model_files)
	{
	my $model = new LangIDTable('xx', 'xx');
	$model->load("$model_dir/$file");
	$lang_codes->{$model->{'code2'}} = $model->{'name'};
	push @models, $model;
	}

my $parser = new HCParser;

if (@hc_twitter_files == 1 and $hc_twitter_files[0] =~ /\.manifest$/)
	{
	open my $in, $hc_twitter_files[0];
	@hc_twitter_files = map { s/[\r\n]+//; $_ } <$in>;
	close $in;
	}


foreach my $hc_file (@hc_twitter_files)
	{
	$hc_file =~ s/\/cygdrive\/(\w)/\u$1:/i;
	unless ($hc_file =~ /(?:^|\\|\/)(\w{2,3})_twitter.txt$/i)
		{
		warn "$hc_file doesn't match expected pattern for twitter data\n";
		next;
		}
	
	my $correct_lid = lc $1;
	
	print "Processing $lang_codes->{$correct_lid} ($correct_lid) ...\n";

	$parser->open($hc_file);
	
	my $classified_as = { $correct_lid => 0 };
	my $total = 0;
	
	my $batch_index = 0;
	my $text = '';
	
	while (my $values = $parser->read())
		{
		if (++$batch_index < $batch_size)
			{
			$text .= "$values->{text}\n";
			}
		else
			{
			$text .= "$values->{text}\n";

			# test each model against this file
			my @scored_models = ();
			foreach my $model (@models)
				{
				push @scored_models, [ $model, $model->score($text) ];
				}
			
			@scored_models = sort { $b->[1] <=> $a->[1] } @scored_models;
			
			my $class_name = $scored_models[0]->[0]->{'code2'};
			if ($scored_models[0]->[1] < 0.1 * LangIDTable::get_max_score())
				{
				$class_name = 'unknown';
				}
			
			$classified_as->{$class_name}++;
			$total++;
			
			throttled_print('accuracy', 2, sprintf("Precision for $correct_lid thus far: %.1f%% (%d/%d)\n", 100 * $classified_as->{$correct_lid} / $total, $classified_as->{$correct_lid}, $total));
			$text = "";
			$batch_index = 0;
			
			last if ($total >= 10000 / $batch_size);
			}
		}
		
	print "$correct_lid $total classifications:\n";
	my @langs = sort { $classified_as->{$b} <=> $classified_as->{$a} } keys %$classified_as;
	for (my $i = 0; $i < @langs and $i < 10; $i++)
		{
		printf "\t%s: %.1f%% (%d)\n", $langs[$i], 100 * $classified_as->{$langs[$i]} / $total, $classified_as->{$langs[$i]};
		}
	
	$parser->close($hc_file);
	
	$overalls->{$correct_lid} = $classified_as;
	}

print "\n";

my $total = 0;
my $correct = 0;
foreach my $lid (keys %$overalls)
	{
	my $total_lang = 0;
	foreach my $class (keys %{$overalls->{$lid}})
		{
		if ($class eq $lid)
			{
			$correct += $overalls->{$lid}->{$class};
			}
		$total += $overalls->{$lid}->{$class};
		$total_lang += $overalls->{$lid}->{$class};
		}
	printf "Accuracy for %s (%s): %.1f%%\n", $lang_codes->{$lid}, $lid, 100 * $overalls->{$lid}->{$lid} / $total_lang;
	}
printf "\nOverall accuracy: %.1f%%\n", 100 * $correct / $total;



sub throttled_print
	{
	my ($id, $interval, $message) = @_;
	
	if (not defined $throttled_print_tracker->{$id} or time() - $throttled_print_tracker->{$id} >= $interval)
		{
		print $message;
		$throttled_print_tracker->{$id} = time();
		}
	}